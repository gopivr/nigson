FROM nginxinc/nginx-unprivileged:stable-alpine@sha256:f5116ed4606f3eb015dd4a8e28cb9b351476dc0ad4bc6e9f93b19cec130cdf03
COPY --chown=101:101 . /usr/share/nginx/html/
USER 101:101
EXPOSE 8080 8443
ENTRYPOINT ["nginx"]
CMD ["-g", "daemon off;"]
