import { type NextRequest, NextResponse } from 'next/server';

export async function middleware(request: NextRequest) {
  try {
    const BASE_API_URL = process.env.NEXT_PUBLIC_URL_API;
    const authenPages = [
      '/authen/login',
      '/authen/register',
      '/authen/forgot-password',
      '/authen/reset-password',
    ];
    const token = request.cookies.get('token')?.value || '';
    const prePath = request.cookies.get('prePath')?.value || '';
    const apiResponse = await fetch(`${BASE_API_URL}/users/authen/me?token=${encodeURIComponent(token)}`);
    const json = await apiResponse.json();
  
    const pathname = request.nextUrl.pathname;
    if (json.status === 401 && !authenPages.includes(pathname)) {
      return NextResponse.redirect(new URL('/authen/login', request.url));
    }

    if (json.data && authenPages.includes(pathname)) {
      return NextResponse.redirect(new URL(prePath, request.url));
    }
  } catch (error) {
    console.log(error);
    return NextResponse.redirect(new URL('/error/500', request.url));
  }

  return NextResponse.next();
}

export const config = {
  matcher: [
    '/authen/:path*',
    '/',
    '/hair-colors/:path*',
    '/hair-styles/:path*',
    '/barbers/:path*',
    '/hair-fast-gan',
    '/history-order',
    '/user-profile',
    '/payment-result',
  ],
};