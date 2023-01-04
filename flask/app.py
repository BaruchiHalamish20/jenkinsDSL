from flask import Flask, request, jsonify
import subprocess
import docker

app = Flask(__name__)

@app.route('/')
def hello():
	return "Hello World!"

@app.route('/containers')
def list_containers():
	client = docker.from_env()
	containers = client.containers.list()
	return jsonify([c.name for c in containers])

@app.route('/flask-health-check')
def flask_health_check():
	return "Ding success "
