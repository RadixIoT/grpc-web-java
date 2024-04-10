# gRPC-Web conformance testing

Runs the [Connect conformance tests](https://github.com/connectrpc/conformance/) for gRPC-Web.

## Manually running conformance tests

From the project directory do `mvn install` then run:

```shell
connectconformance --conf config.yaml --mode server -- java -cp "target\classes;target\lib\*" com.radixiot.grpcweb.conformance.ConformanceMain
```

To run a single test you can use:

```shell
connectconformance --trace --conf config.yaml --mode server --run "**/unary/success" -- java -cp "target\classes;target\lib\*" com.radixiot.grpcweb.conformance.ConformanceMain
```

To run the tests against an external gRPC-Web server (e.g. when debugging) you can add the hostname 
and port as arguments. For example:

```shell
connectconformance --conf config.yaml --mode server -- java -cp "target\classes;target\lib\*" com.radixiot.grpcweb.conformance.ConformanceMain localhost 8081
```
