function handler(event) {
  var request = event.request;
  var uri = request.uri;
  if (/^\/(internal|actuator)(\/|$)/.test(uri)) {
    return { statusCode: 403, statusDescription: 'Forbidden', headers: { 'cache-control': { value: 'no-store' } } };
  }
  // Only SPA navigation gets index.html. Missing remote entries/assets retain real errors.
  if (uri.indexOf('/remotes/') !== 0 && uri.indexOf('/assets/') !== 0 && uri.split('/').pop().indexOf('.') === -1) {
    request.uri = '/index.html';
  }
  return request;
}
