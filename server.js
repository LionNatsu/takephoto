'use strict'

const PORT = 8080
let   http = require('http')
let   fs   = require('fs')

http.createServer((req, res) => {
    if (req.url == "/upload") {
        let bodyList = []
        let bodyLen = 0
        req.on('data', (chunk) => {
            bodyList.push(chunk)
            bodyLen += chunk.length
        })
        req.on('end', () => {
            let body = Buffer.concat(bodyList, bodyLen)
            console.log('server: decapsulate')
            let ct = req.headers['content-type']
            let p = ct.toLowerCase().indexOf('boundary')
            if (p == -1) {
                res.writeHead(400)
                res.end('Bad Request')
                return
            }
            p = ct.indexOf('=', p+1)
            if (p == -1) {
                res.writeHead(400)
                res.end('Bad Request')
                return
            }
            let boundary = ct.substring(p+1).replace(/(^\s*)|(\s*$)/g, '')
            let fragment = body.slice(body.indexOf('\r\n\r\n') + 4)
            let messageBody = fragment.slice(0, fragment.indexOf('--'+boundary+'--'))
            
            console.log('server: received file')
            fs.writeFile('received.jpg', messageBody, (err) => {
                if (err) {
                    console.log('fs.writeFile: ' + err)
                    res.writeHead(500)
                    res.end('Internal Server Error: fs')
                    return
                }
                console.log('fs.writeFile: success')
                res.writeHead(200)
                res.end('OK')
            })
        })
    } else if (req.url == "/test") {
        req.on('data', () => {})
        req.on('end', () => {
            console.log('server: test')
            res.writeHead(200)
            res.end()
        })
    } else {
        res.writeHead(404)
        res.end('Not Found')
    }
}).listen(PORT)
