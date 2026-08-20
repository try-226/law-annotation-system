import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || 'http://localhost:8080',
  timeout: 10_000,
})

request.interceptors.request.use(
  (config) => config,
  (error: unknown) => Promise.reject(error),
)

request.interceptors.response.use(
  (response) => response,
  (error: unknown) => Promise.reject(error),
)

export default request
