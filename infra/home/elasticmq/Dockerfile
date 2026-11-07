# ElasticMQ (SQS API) with the LMS queues baked in. Build context: infra/
#   docker build -f infra/home/elasticmq/Dockerfile infra
# Queues are in memory: a restart drops undelivered messages, and Spring Modulith's event
# registry (the outbox) republishes every event not yet marked complete on the next API start.
FROM softwaremill/elasticmq-native:1.7.1
COPY local/elasticmq.conf /opt/elasticmq.conf
