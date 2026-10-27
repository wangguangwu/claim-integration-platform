import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 15000
})

request.interceptors.request.use((config) => {
  config.headers['X-Tenant-Code'] = localStorage.getItem('tenantCode') ?? ''
  config.headers['X-Protocol-Version'] = localStorage.getItem('protocolVersion') ?? ''
  return config
})

export default request
