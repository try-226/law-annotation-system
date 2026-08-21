import axios from 'axios'

const request = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api',
  timeout: 10_000,
  withCredentials: true,
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
