FROM node:22-alpine AS builder

WORKDIR /workspace
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginx:1.28-alpine AS runtime

RUN apk add --no-cache wget
COPY --from=builder /workspace/dist /usr/share/nginx/html
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
EXPOSE 80
