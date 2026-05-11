# syntax=docker/dockerfile:1.7

FROM node:24-alpine AS deps
WORKDIR /app

ENV NEXT_TELEMETRY_DISABLED=1

COPY package.json package-lock.json ./
RUN npm ci

FROM node:24-alpine AS builder
WORKDIR /app

ARG NEXT_PUBLIC_API_BASE_URL
ARG NEXT_PUBLIC_GITHUB_CONNECTION_CLIENT_ID
ARG NEXT_PUBLIC_GITHUB_CONNECTION_REDIRECT_URI

ENV NEXT_TELEMETRY_DISABLED=1 \
    NEXT_PUBLIC_API_BASE_URL=${NEXT_PUBLIC_API_BASE_URL} \
    NEXT_PUBLIC_GITHUB_CONNECTION_CLIENT_ID=${NEXT_PUBLIC_GITHUB_CONNECTION_CLIENT_ID} \
    NEXT_PUBLIC_GITHUB_CONNECTION_REDIRECT_URI=${NEXT_PUBLIC_GITHUB_CONNECTION_REDIRECT_URI}

COPY --from=deps /app/node_modules ./node_modules
COPY . .

RUN npm run build

FROM node:24-alpine AS runner
WORKDIR /app

ENV NODE_ENV=production \
    NEXT_TELEMETRY_DISABLED=1 \
    PORT=3000 \
    HOSTNAME=0.0.0.0

RUN addgroup -S nextjs && adduser -S nextjs -G nextjs

COPY --from=builder --chown=nextjs:nextjs /app/.next/standalone ./
COPY --from=builder --chown=nextjs:nextjs /app/.next/static ./.next/static

USER nextjs

EXPOSE 3000

CMD ["node", "server.js"]
