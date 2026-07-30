FROM node:20-alpine

WORKDIR /app
COPY package.json ./
COPY src ./src

RUN mkdir -p /app/data
VOLUME ["/app/data"]

ENV PORT=8080 \
    DATA_FILE=/app/data/users.json \
    NODE_ENV=production

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD node -e "require('http').get('http://127.0.0.1:'+(process.env.PORT||8080)+'/health',r=>process.exit(r.statusCode===200?0:1)).on('error',()=>process.exit(1))"

CMD ["node", "src/index.js"]
