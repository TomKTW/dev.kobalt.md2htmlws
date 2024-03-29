Markdown to HTML Web Server
===========

This program provides a web server that will host content from markdown files converted to their rendered HTML equivalents and its resources.

---

More information is available [here](https://md2htmlws.kobalt.dev).

---

## Requirements

- Java 17 Runtime

## Usage

```
java -jar server.jar --configPath [CONFIG_PATH]
```

## Options

```
-pt, --configPath [CONFIG_PATH] - Path of configuration JSON file
```

## Example

With following configuration JSON file named `config.json` in same folder as program file:

```
[
  {
    "port": 8080,
    "host": "127.0.0.1",
    "path": "./storage/example.com",
    "name": "example.com"
  },
  {
    "port": 8081,
    "host": "127.0.0.1",
    "path": "./storage/example.net",
    "name": "example.net"
  }
]
```

The following command will start two servers running at ports 8080 and 8081, monitoring contents within `./storage/example.com` and `./storage/example.com`:

```
java -jar server.jar --configPath "./config.json"
```

On initial start, each server will go through folders defined in `path` JSON value. If any folder contains markdown file named as `index.md`, it will create an HTML file named `index.html` that will contain content rendered from markdown file in HTML format. These pages will be accessible from server based on the directory name.

Pages related to HTTP response codes are stored in `status` folder of given `path`. This means that server will not directly provide markdown or HTML files as content, nor it will allow accessing status pages folder.

## License

This program is licensed under AGPL-3.0-only.