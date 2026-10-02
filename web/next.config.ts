import type { NextConfig } from "next";

const apiOrigin = (
  process.env.MEDNET_API_URL ?? "http://localhost:8080"
).replace(/\/+$/, "");

const nextConfig: NextConfig = {
  async rewrites() {
    if (process.env.NODE_ENV !== "development") return [];

    return [
      {
        source: "/api/v1/:path*",
        destination: `${apiOrigin}/api/v1/:path*`,
      },
      {
        source: "/actuator/:path*",
        destination: `${apiOrigin}/actuator/:path*`,
      },
    ];
  },
};

export default nextConfig;
