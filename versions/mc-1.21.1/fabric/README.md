# Fabric 1.21.1

This project reserves the Fabric target. It currently has no loader entry point,
Fabric dependencies, run configurations or distributable JAR.

The port will consume the root `core` and sibling `common` source and resource
artifacts, then define its own Fabric APIs and optional mod dependencies.
Local development dependencies belong in this project's ignored `libs/` directory.
