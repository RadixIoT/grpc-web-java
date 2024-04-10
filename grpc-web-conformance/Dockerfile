FROM alpine:3.19 AS extract
ARG CONNECT_CONFORMANCE_VERSION
RUN apk --no-cache add curl && \
    curl -sL https://github.com/connectrpc/conformance/releases/download/${CONNECT_CONFORMANCE_VERSION}/connectconformance-${CONNECT_CONFORMANCE_VERSION}-Linux-$(uname -m).tar.gz | \
    tar xz

FROM azul/zulu-openjdk:17
COPY --chown=root:root --from=extract connectconformance /root/connectconformance
COPY config.yaml /root/config.yaml
COPY target/classes /root/classes
COPY target/lib /root/lib
COPY target/agent /root/agent
WORKDIR "/root"
ENV CLASSPATH="/root/classes:/root/lib/*"
ENTRYPOINT ["./connectconformance", "--conf", "config.yaml", "--mode", "server", "--known-flaky", "Deadline Propagation/**", "--"]
CMD ["java", "-javaagent:agent/org.jacoco.agent-runtime.jar=destfile=jacoco.exec,includes=com.radixiot.grpcweb.*", "com.radixiot.grpcweb.conformance.ConformanceMain"]
