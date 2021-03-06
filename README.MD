# Docker MQ Proof-of-concept

To demonstrate the use of JMS Message Properties to filter messages on a queue. The demo code is in com.keyur.dockermqpoco.Application class. There are 4 docker containers used in this demo and defined in docker-compose.yml. 
- MQ : server
- pub : message publisher 
- sub1 : message listeners 
- sub2 : message listeners

'pub' will publish messages with a property set with a random value 1 or 2.

'sub1' will only receive messages with property value = 1 and 'sub2' will only receive message with property value = 2

## Requirements

You will need docker-desktop for Windows and Maven installed on your pc

These instructions are for windows but this project will also run on Linux

## How to run

Start Windows Powershell and follow these steps

Clone this repo on to your local pc

```bash
git clone https://github.com/liquidkey/docker-mq-poco.git

cd docker-mq-poco
```

Build the "maven builder" docker image that you can use to build this project
```bash
docker build -t builder/docker-mq-poco:latest --cache-from builder/docker-mq-poco:latest .
```

Build this project using the build image 
```bash
docker run -it --rm -v ${pwd}:/usr/src/app builder/docker-mq-poco -T 1C -o  clean package
```

Execute the docker services defined in docker-compose.yml to demo the mq feature
```bash
docker-compose up -d
```
Shutdown when you no longer need the containers
```bash
docker-compose down
```
