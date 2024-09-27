ARG IMAGE=apachepulsar/pulsar-all
ARG TAG=4.0.0-preview.1
FROM ${IMAGE}:${TAG}
USER 0
RUN apk add openssl
#COPY lib/*.jar /pulsar/lib
USER 10000