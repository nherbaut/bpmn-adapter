from golang
RUN apt update && apt install git openjdk-11-jdk autoconf automake libtool curl make g++ unzip --yes
RUN go get -u github.com/googleapis/gnostic && go get -u github.com/googleapis/gnostic-grpc && go get -u google.golang.org/grpc &&  go get -u github.com/golang/protobuf/protoc-gen-go
WORKDIR /root
RUN git clone https://github.com/protocolbuffers/protobuf.git
WORKDIR /root/protobuf 
RUN git submodule update --init --recursive 
RUN ./autogen.sh
RUN ./configure &&  make install && ldconfig
COPY web/target/web-ui-1.0-SNAPSHOT.jar /root/
CMD ["java", "-jar", "/root/web-ui-1.0-SNAPSHOT.jar"]
