# Garage with its config baked in, so the Portainer stack needs no bind mount.
# The upstream image is FROM scratch (just the binary): no shell, nothing else to patch.
FROM dxflrs/garage:v2.5.0
COPY garage.toml /etc/garage.toml
