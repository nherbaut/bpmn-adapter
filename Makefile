java:
	
	mvn clean package
	sudo docker build -f docker/Dockerfile -t nherbaut/bpmn-openapi-adapter --label "bmpn-adapter"  .

