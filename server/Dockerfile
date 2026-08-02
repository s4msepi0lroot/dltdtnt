FROM node:20-alpine
WORKDIR /app
COPY package.json ./
RUN npm install --omit=dev
COPY src ./src
ENV PORT=8080
ENV DDN_DATA_DIR=/app/data
VOLUME ["/app/data"]
EXPOSE 8080
CMD ["node", "src/index.js"]
