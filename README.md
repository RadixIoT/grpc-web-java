# gRPC-Web for Java

## Description

This project provides a [gRPC-Web](https://github.com/grpc/grpc/blob/master/doc/PROTOCOL-WEB.md) reverse proxy via a
Java servlet. It accepts HTTP/1.1 connections and forwards them via a gRPC client to a (HTTP/2) gRPC server.

It is based off code
[initially contributed](https://github.com/grpc/grpc-web/tree/063bb42d85c293863d\b075457b7b4117184fc9f8/src/connector)
to the grpc-web project which was deprecated and removed. The code has been heavily modified and now conforms with
the [Buf Connect conformance](https://github.com/connectrpc/conformance) tests for gRPC-Web.

## License

This project is licensed under the [Apache 2.0 license](https://www.apache.org/licenses/LICENSE-2.0.txt).
