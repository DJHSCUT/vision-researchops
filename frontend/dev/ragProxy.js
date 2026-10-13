const loopbacks = new Set(['127.0.0.1', '::1', '::ffff:127.0.0.1'])
const hosts = new Set(['127.0.0.1:5173', 'localhost:5173'])
const origins = new Set(['http://127.0.0.1:5173', 'http://localhost:5173'])

export const ragAskPath = '^/dev/rag/ask(?:\\?|$)'

export function guardLocalRagProxy(req, res, next) {
  if (!new RegExp(ragAskPath).test(req.url || '')) return next()
  const forwarded = ['forwarded', 'x-forwarded-for', 'x-real-ip'].some(name => req.headers[name] !== undefined)
  if (!loopbacks.has(req.socket.remoteAddress) || !hosts.has(req.headers.host)
    || forwarded || (req.headers.origin && !origins.has(req.headers.origin))) {
    res.statusCode = 403
    res.setHeader('Content-Type', 'application/json; charset=utf-8')
    res.end(JSON.stringify({ code: 403, message: '科研知识问答仅允许可信本机开发访问', data: null }))
    return
  }
  next()
}

export function localRagProxyGuard() {
  return {
    name: 'local-rag-proxy-guard', apply: 'serve',
    configureServer(server) { server.middlewares.use(guardLocalRagProxy) },
  }
}
