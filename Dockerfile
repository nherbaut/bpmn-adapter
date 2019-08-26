from golang
RUN apt update && apt install git openjdk-11-jdk autoconf automake libtool curl make g++ unzip --yes
RUN go get -u github.com/googleapis/gnostic && go get -u github.com/googleapis/gnostic-grpc && go get -u google.golang.org/grpc &&  go get -u github.com/golang/protobuf/protoc-gen-go 
RUN go get -u github.com/grpc-ecosystem/grpc-gateway/protoc-gen-grpc-gateway
RUN wget https://github.com/protocolbuffers/protobuf/releases/download/v3.9.1/protoc-3.9.1-linux-x86_64.zip
RUN unzip protoc-3.9.1-linux-x86_64.zip  -d /usr/local
COPY web/target/web-ui-1.0-SNAPSHOT.jar /root/
CMD ["java", "-jar", "/root/web-ui-1.0-SNAPSHOT.jar"]
