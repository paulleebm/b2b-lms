# One-off job that prepares Garage through its admin API: node layout, the API's access key and
# the bucket. Safe to run on every deploy (each step checks before it changes anything).
FROM alpine:3.22
RUN apk add --no-cache curl jq
COPY --chmod=755 init.sh /init.sh
ENTRYPOINT ["/init.sh"]
