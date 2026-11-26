import type { NextConfig } from "next";

// 브라우저는 같은 출처의 /api/*를 부르고, Next.js가 Spring 서버로 넘긴다 (K2, API 설계 1-1).
// 세션 쿠키 · CSRF 쿠키가 같은 출처로 오가게 하기 위해서다.
const apiBaseUrl = process.env.API_BASE_URL ?? "http://localhost:8080";

const nextConfig: NextConfig = {
  async rewrites() {
    return [{ source: "/api/:path*", destination: `${apiBaseUrl}/api/:path*` }];
  },
};

export default nextConfig;
