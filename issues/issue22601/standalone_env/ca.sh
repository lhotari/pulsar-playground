openssl req -x509 -new -nodes -keyout ca-key -subj "/CN=CARoot" -days 365 -out ca-cert
keytool -keystore truststore.jks -alias CARoot -import -file ca-cert -storepass password -noprompt
