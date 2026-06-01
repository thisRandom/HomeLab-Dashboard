import axios from 'axios'

const request = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

export default request
