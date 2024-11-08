import { BarberController } from '@barber/barber.controller';
import { BarberGrpcController } from '@barber/barber.grpc.controller';
import { BarberRepository } from '@barber/barber.repository';
import { BarberService } from '@barber/barber.service';
import { Module } from '@nestjs/common';

@Module({
  controllers: [BarberController, BarberGrpcController],
  providers: [BarberService, BarberRepository],
  exports: [BarberService, BarberRepository],
})
export class BarberModule {}
