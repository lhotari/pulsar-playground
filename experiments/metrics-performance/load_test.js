import http from 'k6/http';
import { sleep } from 'k6';

export let options = {
    vus: 100, // Number of virtual users
    iterations: 10000, // Total number of request iterations
};

export default function () {
    http.get('http://localhost:8080/metrics/');
}