

- in de demo zorgt istio (service mesh) er voor dat elke pod binnen de namespace een `Envoy Proxy` sidecar container (initContainer met restartPolicy="Always") krijgt geïnjecteerd omdat de namespace is gelabeled met `istio-injection=enabled`
- Als `Pod A`, `Pod B` aanroept dan kaapt `Envoy Proxy A` het uitgaande verkeer van een `pod A`  
- **De Handshake:** Proxy A opent een TLS-verbinding met de Envoy-proxy van de backend (Proxy B). Proxy B laat zijn certificaat zien aan Proxy A. Proxy A controleert dit tegen zijn *truststore* (het Istio CA-certificaat). Omdat het *mutual* (wederzijdse) TLS is, vraagt Proxy B ook om het certificaat van Proxy A. Proxy B valideert dit op zijn beurt tegen zijn eigen *truststore*.
- **De Versleutelde Tunnel:** Als beide certificaten geldig zijn, wordt er een encrypted tunnel opgezet. De data wordt veilig over het netwerk getransporteerd.