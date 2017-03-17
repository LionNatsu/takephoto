# takephoto
An Android application to simply upload the photo captured by camera.

## Usage

1. Run the server[1] on any computer can be reached by network, say `192.168.0.1:8080`
2. Start the app
3. Enter the address with port (`192.168.0.1:8080`) into the edit field
4. Click on "TEST"
    - If the test has succeeded, let's give it a go. Flip to the main page, click on the screen.
    - If the test failed, check your network setting and the server-side program.

[1]: See [`server.js`](server.js) (Node.js script)

## Server-side

The server MUST have implemented HTTP interface and POST method.

It MUST have an interface to post at `/upload`. Client will use the parameter
`Content-Type: application/octet-stream; charset=utf-8` to post the request.
In the `form-data`, `name` is `img`, there is no guarantee that `filename` is valid.

It SHOULD have an interface to post at `/test`. Client will do the similar request as `/upload`.
The difference is `name` in `form-data` is `test`. The data from client is meaningless data.
