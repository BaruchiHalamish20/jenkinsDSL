#!/bin/bash
#support dynamic flask server:
#envsubst '$FLASK_SERVER_ADDR' < /tmp/default.conf > /etc/nginx/conf.d/default.conf && nginx -g 'daemon off;'
echo "FLASK_SERVER_ADDR env is: $FLASK_SERVER_ADDR"
cat /tmp/default.conf  | sed s/\$FLASK_SERVER_ADDR/$FLASK_SERVER_ADDR/s > /etc/nginx/conf.d/default.conf 
 nginx -g 'daemon off;'
