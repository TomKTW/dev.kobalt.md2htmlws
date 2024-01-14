Markdown to HTML Web Server
===========

This program provides a web server that will host content from markdown files converted to their rendered HTML equivalents and its resources.

## Requirements

- Java 17 Runtime

## Usage

```
java -jar server.jar --port [SERVER_PORT] --host [SERVER_HOST]
```

## Options

```
-pt, --port [SERVER_PORT] - Port to host the server at

-ht, --host [SERVER_HOST] - Host value (127.0.0.1 for private, 0.0.0.0 for public access)
```

## Example

The following command will start a private server running at port 8080:

```
java -jar server.jar --port 8080 --host 127.0.0.1
```

## License

This program is licensed under AGPL-3.0-only.