.PHONY: java docker push

java:
	mvn clean package

docker: 
	sudo docker build -t nherbaut/bpmn-openapi-adapter --label "bmpn-adapter"  .

run:
	sudo docker run -p 8484:8484 -d nherbaut/bpmn-openapi-adapter:latest 

push: docker
	sudo docker push nherbaut/bpmn-openapi-adapter

