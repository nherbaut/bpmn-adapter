java:
	
	mvn clean package
	sudo docker build -t nherbaut/bpmn-openapi-adapter --label "bmpn-adapter"  .

