tls {
  http = true
  rpc  = true

  ca_file   = "/etc/nomad.d/ssl/nomad-ca.pem"
  cert_file = "/etc/nomad.d/ssl/server.pem"
  key_file  = "/etc/nomad.d/ssl/server-key.pem"

  verify_server_hostname = true
  verify_https_client    = true
}

