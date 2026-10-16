# nginx web server that runs as a normal user (not root)
FROM nginxinc/nginx-unprivileged:1.31-alpine
COPY nginx.conf /etc/nginx/conf.d/default.conf
# only the website files (so nginx.conf / Dockerfile are never served)
COPY *.html manifest.json service-worker.js /usr/share/nginx/html/
COPY css /usr/share/nginx/html/css
COPY js /usr/share/nginx/html/js
COPY icons /usr/share/nginx/html/icons
