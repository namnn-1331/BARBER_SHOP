package barbershop.order_service.services.impl;

import barber.Barber;
import barber.BarberServiceGrpc;
import barber.GetAllBarberRequest;
import barber.GetDetailBarberRequest;
import barbershop.order_service.Utils.Utils;
import barbershop.order_service.dtos.request.FindOrderInfoRequest;
import barbershop.order_service.dtos.request.GetListOrderByUserRequest;
import barbershop.order_service.dtos.request.PaymentRequest;
import barbershop.order_service.dtos.response.BaseResponse;
import barbershop.order_service.dtos.response.FieldErrorsResponse;
import barbershop.order_service.dtos.response.PaginationResponse;
import barbershop.order_service.entities.Order;
import barbershop.order_service.enums.TimeZone;
import barbershop.order_service.exception.ResourceNotFoundException;
import barbershop.order_service.repositories.OrderRepository;
import barbershop.order_service.services.OrderService;
import barbershop.order_service.services.RedisService;
import block_time.BlockTimeServiceGrpc;
import block_time.CheckBlockTimeRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import hairColor.GetDetailHairColorRequest;
import hairColor.HairColor;
import hairColor.HairColorServiceGrpc;
import hairStyle.GetDetailHairStyleRequest;
import hairStyle.HairStyle;
import hairStyle.HairStyleServiceGrpc;
import lombok.extern.slf4j.Slf4j;
import net.devh.boot.grpc.client.inject.GrpcClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import payment.*;
import user.UserServiceGrpc;

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Service
public class OrderServiceImpl implements OrderService {
    @GrpcClient("user-grpc-server")
    private UserServiceGrpc.UserServiceBlockingStub userServiceBlockingStub;

    @GrpcClient("block-time-grpc-server")
    private BlockTimeServiceGrpc.BlockTimeServiceBlockingStub blockTimeServiceBlockingStub;

    @GrpcClient("barber-grpc-server")
    private BarberServiceGrpc.BarberServiceBlockingStub barberServiceBlockingStub;

    @GrpcClient("payment-grpc-server")
    private PaymentServiceGrpc.PaymentServiceBlockingStub paymentServiceBlockingStub;

    @GrpcClient("hair-style-grpc-server")
    private HairStyleServiceGrpc.HairStyleServiceBlockingStub hairStyleServiceBlockingStub;

    @GrpcClient("hair-color-grpc-server")
    private HairColorServiceGrpc.HairColorServiceBlockingStub hairColorServiceBlockingStub;

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private RedisService redisService;

    @Autowired
    private ObjectMapper objectMapper;

    private void checkValidDateAndTimeRequest(FindOrderInfoRequest findOrderInfoRequest) throws Exception {
        Date correctDate = Utils.parseDate(findOrderInfoRequest.getDate()+" "+ findOrderInfoRequest.getTime(), "yyyy-MM-dd HH:mm", TimeZone.ASIA_HCM.value());
        List<FieldErrorsResponse.FieldError> listFieldErrors = new ArrayList<>();
        if (correctDate == null) {
            listFieldErrors.add(
                    FieldErrorsResponse.FieldError.builder()
                            .field("date; time")
                            .message("Invalid date and time")
                            .resource("FindBarberRequest")
                            .build()
            );
            throw FieldErrorsResponse
                    .builder()
                    .errors(listFieldErrors)
                    .build();
        }

        if (!Utils.isDateInRange(findOrderInfoRequest.getDate()+" "+ findOrderInfoRequest.getTime(), "yyyy-MM-dd HH:mm", TimeZone.ASIA_HCM.value(), 0, 6)) {
            listFieldErrors.add(
                    FieldErrorsResponse.FieldError.builder()
                            .field("date; time")
                            .message("Error range date and time, date and time in range [0; 6] days")
                            .resource("FindBarberRequest")
                            .build()
            );
            throw FieldErrorsResponse
                    .builder()
                    .errors(listFieldErrors)
                    .build();
        }

        List<String> timeSlots = Utils.generateTimeSlots();
        if (!timeSlots.contains(findOrderInfoRequest.getTime())) {
            listFieldErrors.add(
                    FieldErrorsResponse.FieldError.builder()
                            .field("time")
                            .message("Invalid time slot")
                            .resource("FindBarberRequest")
                            .build()
            );
            throw FieldErrorsResponse
                    .builder()
                    .errors(listFieldErrors)
                    .build();
        }

        boolean isBlocked = blockTimeServiceBlockingStub.checkBlockTime(CheckBlockTimeRequest.newBuilder()
                .setDate(findOrderInfoRequest.getDate())
                .setTime(findOrderInfoRequest.getTime())
                .build()
        ).getIsBlocked();

        if (isBlocked) {
            listFieldErrors.add(
                    FieldErrorsResponse.FieldError.builder()
                            .field("date; time")
                            .message("Date and time is blocked")
                            .resource("FindBarberRequest")
                            .build()
            );
            throw FieldErrorsResponse
                    .builder()
                    .errors(listFieldErrors)
                    .build();
        }
    }

    @Override
    public BaseResponse findOrderInfo(FindOrderInfoRequest findOrderInfoRequest) throws Exception {
        checkValidDateAndTimeRequest(findOrderInfoRequest);
        List<Barber> barbers = barberServiceBlockingStub.getAllBarber(GetAllBarberRequest.newBuilder().build()).getDataList();
        List<Integer> barberIds = new ArrayList<>();
        for (Barber barber : barbers) {
            barberIds.add(barber.getId());
        }
        barberIds = orderRepository.findBarberIds(barberIds);

        if (barberIds.size() == 0) {
            return new BaseResponse(null);
        }

        int barberId = barberIds.get(0);
        Barber barber = null;
        for (Barber b : barbers) {
            if (b.getId() == barberId && b.getActive()) {
                barber = b;
            }
        }

        if (barber == null) {
            return new BaseResponse(null);
        }

        // find hairStyle and hairColor
        List<FieldErrorsResponse.FieldError> listFieldErrors = new ArrayList<>();
        HairStyle hairStyle = null;
        HairColor hairColor = null;
        Map<String, Object> hairStyleMap = new LinkedHashMap<>();
        Map<String, Object> hairColorMap = new LinkedHashMap<>();

        try {
            hairStyle = hairStyleServiceBlockingStub.getDetailHairStyle(GetDetailHairStyleRequest.newBuilder()
                    .setId(findOrderInfoRequest.getHairStyleId())
                    .build()).getHairStyle();

            if (findOrderInfoRequest.getHairColorId() != 0) {
                hairColor = hairColorServiceBlockingStub.getDetailHairColor(GetDetailHairColorRequest.newBuilder()
                        .setId(findOrderInfoRequest.getHairColorId())
                        .build()).getHairColor();
            }
        } catch (Exception exception) {
            log.error("ERROR", exception);
            listFieldErrors.add(
                    FieldErrorsResponse.FieldError.builder()
                            .field("hair style, hair color")
                            .message("Hair style or hair color not found")
                            .resource("PaymentRequest")
                            .build()
            );
            throw FieldErrorsResponse
                    .builder()
                    .errors(listFieldErrors)
                    .build();
        }

        int amount = hairStyle.getPrice();

        hairStyleMap.put("id", hairStyle.getId());
        hairStyleMap.put("name", hairStyle.getName());
        hairStyleMap.put("price", hairStyle.getPrice());
        hairStyleMap.put("active", hairStyle.getActive());
        if (hairStyle.getDiscount() != null) {
            Map<String, Object> discountMap = new LinkedHashMap<>();
            discountMap.put("unit", hairStyle.getDiscount().getUnit());
            discountMap.put("value", hairStyle.getDiscount().getValue());
            hairStyleMap.put("discount", discountMap);
            if (hairStyle.getDiscount().getUnit().equals("%")) {
                amount = amount * (100 - hairStyle.getDiscount().getValue()) / 100;
            } else {
                amount = amount - hairStyle.getDiscount().getValue();
            }
        }

        if (hairColor != null) {
            hairColorMap.put("id", hairColor.getId());
            hairColorMap.put("color", hairColor.getColor());
            hairColorMap.put("price", hairColor.getPrice());
            hairColorMap.put("active", hairColor.getActive());
            hairColorMap.put("colorCode", hairColor.getColorCode());
            amount = amount + hairColor.getPrice();
        }

        Map<String, Object> barberMap = new LinkedHashMap<>();
        barberMap.put("id", barber.getId());
        barberMap.put("name", barber.getName());
        barberMap.put("avatar", barber.getImg());
        barberMap.put("active", barber.getActive());

        Map<String, Object> map = new LinkedHashMap<>();
        map.put("barber", barberMap);
        map.put("hairStyle", hairStyleMap);
        map.put("user", findOrderInfoRequest.getUser());
        if (hairColor != null) {
            map.put("hairColor", hairColorMap);
        }
        map.put("amount", amount);

        BaseResponse baseResponse = new BaseResponse(map);
        return baseResponse;
    }

    @Transactional
    @Override
    public BaseResponse payment(PaymentRequest paymentRequest) throws Exception {
        List<FieldErrorsResponse.FieldError> listFieldErrors = new ArrayList<>();

        if (!paymentRequest.getPaymentType().equals("VNPAY") && !paymentRequest.getPaymentType().equals("MOMO")) {
            listFieldErrors.add(
                    FieldErrorsResponse.FieldError.builder()
                            .field("payment type")
                            .message("Payment type must be VNPAY or MOMO")
                            .resource("PaymentRequest")
                            .build()
            );
            throw FieldErrorsResponse
                    .builder()
                    .errors(listFieldErrors)
                    .build();
        }

        Map<String, Object> orderInfoMap = (Map<String, Object>) findOrderInfo(FindOrderInfoRequest.builder()
                .date(paymentRequest.getDate())
                .time(paymentRequest.getTime())
                .hairStyleId(paymentRequest.getHairStyleId())
                .hairColorId(paymentRequest.getHairColorId())
                .build()).getData();

        Map<String, Object> barberMap = (Map<String, Object>) orderInfoMap.get("barber");
        Map<String, Object> hairStyleMap = (Map<String, Object>) orderInfoMap.get("hairStyle");
        Map<String, Object> hairColorMap = (Map<String, Object>) orderInfoMap.get("hairColor");
        int amount = (int) orderInfoMap.get("amount");

        Map<String, Object> orderMap = new LinkedHashMap<>();
        orderMap.put("user", paymentRequest.getUser());
        orderMap.put("schedule", paymentRequest.getDate()+" "+paymentRequest.getTime());
        orderMap.put("cutted", false);
        orderMap.put("barber", barberMap);
        orderMap.put("hairStyle", hairStyleMap);
        orderMap.put("hairColor", hairColorMap);
        orderMap.put("orderTime", Utils.toDateStringWithFormatAndTimezone(new Date(), "yyyy-MM-dd HH:mm:ss", TimeZone.ASIA_HCM.value()));
        orderMap.put("amount", amount);
        orderMap.put("status", "Success");
        orderMap.put("paymentType", paymentRequest.getPaymentType());

        String orderUUID = UUID.randomUUID().toString();
        redisService.setValue(orderUUID, objectMapper.writeValueAsString(orderMap), 1800000, TimeUnit.MILLISECONDS);

        TransactionResponse transactionResponse = paymentServiceBlockingStub.transaction(TransactionRequest.newBuilder()
                        .setOrderUUID(orderUUID)
                        .setPaymentType(paymentRequest.getPaymentType())
                        .setAmount(amount)
                .build());

        return new BaseResponse(Map.of("paymentUrl", transactionResponse.getPaymentUrl()));
    }

    @Override
    public PaginationResponse getListOrderByUser(GetListOrderByUserRequest getListOrderByUserRequest) throws Exception {
        List<FieldErrorsResponse.FieldError> listFieldErrors = new ArrayList<>();

        if (getListOrderByUserRequest.getSortBy() != null) {
            String regex = "^(asc|desc)$";
            Pattern pattern = Pattern.compile(regex);
            Matcher matcher = pattern.matcher(getListOrderByUserRequest.getSortBy());
            if (!matcher.matches()) {
                listFieldErrors.add(
                        FieldErrorsResponse.FieldError.builder()
                                .field("sort by")
                                .message("Sort by must be most recent or longest")
                                .resource("GetListOrderByUserRequest")
                                .build()
                );
                throw FieldErrorsResponse
                        .builder()
                        .errors(listFieldErrors)
                        .build();
            }
        }

        int orderId = 0;
        try {
            orderId = Integer.parseInt(getListOrderByUserRequest.getCodeOrHairStyle().split("BBSOD")[1]);
        } catch (Exception exception) {}

        List<Order> orders = orderRepository.getListOrderByUser(
                orderId,
                getListOrderByUserRequest
        );

        List<Map<String, Object>> orderMapList = new ArrayList<>();
        List<Integer> orderIds = new ArrayList<>();
        for (Order order : orders) {
            orderIds.add(order.getId());
        }

        List<Payment> paymentGrpcs = (paymentServiceBlockingStub.getListPaymentByOrderIds(
                GetListPaymentByOrderIdsRequest.newBuilder()
                        .addAllOrderIds(orderIds)
                        .build()
        )).getPaymentsList();

        for (Order order : orders) {
            Map<String, Object> orderMap = new LinkedHashMap<>();
            orderMap.put("id", order.getId());
            Map<String, Object> hairStyleMap = objectMapper.readValue(order.getHairStyle(), LinkedHashMap.class);
            Map<String, Object> hairColorMap = objectMapper.readValue(order.getHairColor(), LinkedHashMap.class);
            orderMap.put("hairStyle", hairStyleMap.get("name"));
            if (hairColorMap != null) {
                orderMap.put("hairColor", Map.of("colorCode", hairColorMap.get("colorCode"), "color", hairColorMap.get("color")));
            }
            Payment paymentGrpc = findPaymentGrpcByOrderId(paymentGrpcs, order.getId());
            orderMap.put("orderTime", Utils.toDateStringWithFormatAndTimezone(order.getOrderTime(), "yyyy-MM-dd HH:mm:ss", TimeZone.ASIA_HCM.value()));
            orderMap.put("paymentType", paymentGrpc.getType());
            orderMap.put("amount", paymentGrpc.getAmount());
            orderMapList.add(orderMap);
        }

        int totalRecords = orderRepository.countOrderByUser(orderId, getListOrderByUserRequest);

        PaginationResponse paginationResponse = new PaginationResponse();
        paginationResponse.setData(orderMapList);
        paginationResponse.setMeta(
                PaginationResponse.Meta.builder()
                        .items(Integer.parseInt(getListOrderByUserRequest.getItems()))
                        .page(Integer.parseInt(getListOrderByUserRequest.getPage()))
                        .totalRecords(totalRecords)
                        .build()
        );

        return paginationResponse;
    }

    private Payment findPaymentGrpcByOrderId(List<Payment> paymentGrpcs, int orderId) {
        for (Payment paymentGrpc: paymentGrpcs) {
            if (paymentGrpc.getOrderId() == orderId) {
                return paymentGrpc;
            }
        }
        return null;
    }

    @Override
    public BaseResponse getOrderById(String orderIdString, Map<String, Object> user) throws Exception {
        List<FieldErrorsResponse.FieldError> listFieldErrors = new ArrayList<>();

        int orderId = 0;
        try {
            orderId = Integer.parseInt(orderIdString);
        } catch (Exception exception) {
            listFieldErrors.add(
                    FieldErrorsResponse.FieldError.builder()
                            .field("Order id")
                            .message("Order id must be integer")
                            .resource("Path Variable")
                            .build()
            );
            throw FieldErrorsResponse
                    .builder()
                    .errors(listFieldErrors)
                    .build();
        }

        if (orderId < 1) {
            listFieldErrors.add(
                    FieldErrorsResponse.FieldError.builder()
                            .field("Order id")
                            .message("Order id must be greater than 0")
                            .resource("Path Variable")
                            .build()
            );
            throw FieldErrorsResponse
                    .builder()
                    .errors(listFieldErrors)
                    .build();
        }

        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            throw new ResourceNotFoundException("Order not found");
        }

        Payment payment = findPaymentGrpcByOrderId(
                paymentServiceBlockingStub.getListPaymentByOrderIds(
                        GetListPaymentByOrderIdsRequest.newBuilder()
                                .addAllOrderIds(List.of(order.getId()))
                                .build()
                ).getPaymentsList(),
                order.getId()
        );

        Map<String, Object> orderMap = new LinkedHashMap<>();
        Map<String, Object> hairStyleMap = objectMapper.readValue(order.getHairStyle(), LinkedHashMap.class);
        Map<String, Object> hairColorMap = objectMapper.readValue(order.getHairColor(), LinkedHashMap.class);
        Map<String, Object> barberMap = objectMapper.readValue(order.getBarber(), LinkedHashMap.class);

        Barber barber = barberServiceBlockingStub.getDetailBarber(
                GetDetailBarberRequest.newBuilder()
                        .setId((int) barberMap.get("id"))
                        .build()
        ).getBarber();

        barberMap.put("avatar", barber.getImg());

        orderMap.put("id", order.getId());
        orderMap.put("amount", payment.getAmount());
        orderMap.put("status", payment.getStatus());
        orderMap.put("paymentType", payment.getType());
        orderMap.put("cutted", order.isCutted());
        orderMap.put("schedule", Utils.toDateStringWithFormatAndTimezone(order.getSchedule(), "yyyy-MM-dd HH:mm", TimeZone.ASIA_HCM.value()));
        orderMap.put("orderTime", Utils.toDateStringWithFormatAndTimezone(order.getOrderTime(), "yyyy-MM-dd HH:mm:ss", TimeZone.ASIA_HCM.value()));
        orderMap.put("user", user);
        orderMap.put("barber", barberMap);
        orderMap.put("hairStyle", hairStyleMap);
        orderMap.put("hairColor", hairColorMap);

        return new BaseResponse(orderMap);
    }
}
