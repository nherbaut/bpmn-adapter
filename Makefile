.PHONY: java docker push

java:
	mvn clean package

docker: 
	sudo docker build -t nherbaut/bpmn-openapi-adapter --label "bmpn-adapter"  .

run:
	sudo docker run -p 8484:8484 -ti nherbaut/bpmn-openapi-adapter:latest bash

push: docker
	sudo docker push nherbaut/bpmn-openapi-adapter

