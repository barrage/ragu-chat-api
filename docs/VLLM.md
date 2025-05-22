# vLLM Multi-Model Deployment on a Single Machine

This guide walks you through deploying multiple vLLM instances on a single Linux machine, each serving a different model
on dedicated GPUs, behind a reverse proxy (nginx).

## **Prerequisites**

- Linux OS with CUDA installed
- Compatible version of Python 3.12+
- Root or sudo access
- Hugging Face account (for private models)

---

## Installation

1. Install Python venv
   ```bash
   apt install python3.12-venv -y
   ```

2. Create and activate the virtual environment
   ```bash
   python3 -m venv /opt/vllm/venv
   source /opt/vllm/venv/bin/activate
   ```

3. Install vLLM
   ```bash
   pip install vllm
   ```

## Create a Dedicated User

Create a non-login system user to run vLLM:

```bash
adduser vllm --shell=/bin/false --disabled-password
```

## Sample Model Setup: Qwen 72B

1. Create model scripts folder
   ```bash
   mkdir -p /opt/vllm/scripts
   ```

2. Create model launch script
   ```bash
   cat << 'EOF' > /opt/vllm/scripts/vllm_qwen.sh
   #!/bin/bash
   source /opt/vllm/venv/bin/activate
   vllm serve Qwen/Qwen2.5-VL-72B-Instruct \
      --host 127.0.0.1 \
      --port 9001 \
      --tensor-parallel-size 4
   EOF

   chmod +x /opt/vllm/scripts/vllm_qwen.sh
   chown vllm: /opt/vllm/scripts/vllm_qwen.sh
   ```

Note: Some models require a Hugging Face token.
Login as the vllm user and authenticate:

```bash
sudo -u vllm -i
huggingface-cli login
```

## Create Systemd Service

Create the service file for the model:

```bash
cat << 'EOF' > /etc/systemd/system/vllm-qwen.service
[Unit]
Description=vLLM Server - Qwen 2.5 VL 72B Instruct
After=network.target

[Service]
Type=simple
User=vllm
WorkingDirectory=/opt/vllm
ExecStart=/opt/vllm/scripts/vllm_qwen.sh
Restart=always
RestartSec=5
Environment=PYTHONUNBUFFERED=1
Environment=CUDA_VISIBLE_DEVICES=4,5,6,7

[Install]
WantedBy=multi-user.target
EOF
```

Reload systemd and start the service:

```bash
systemctl daemon-reload
systemctl enable vllm-qwen.service
systemctl restart vllm-qwen.service
```

## Reverse Proxy with Nginx

1. Install nginx
   ```bash
   apt install nginx -y
   ```

2. Configure Virtual Host
   Create a new nginx config:

   ```bash
   cat << 'EOF' > /etc/nginx/sites-enabled/00-vllm.vhost
   server {
       listen 80;
       server_name your.domain.com;

       location /qwen/ {
           rewrite ^/qwen(/.*)$ $1 break;
           proxy_pass http://localhost:9001;
       }

       location / {
           return 404;
       }
   }
   EOF
   ```

Test and restart:

```bash
nginx -t && systemctl restart nginx
```

## HTTPS with Let's Encrypt

1. Add A record to point your domain to the machine.

2. Install Certbot
    ```bash
    apt install certbot python3-certbot-nginx -y
    ```

3. Generate certificates
    ```bash
    certbot --nginx
    ```

## Multiple Models: Example Setup

You can run additional vLLM processes on different ports and GPUs:

```bash
# Terminal 1
CUDA_VISIBLE_DEVICES=0,1 vllm serve Qwen/Qwen2.5-1.5B-Instruct --port 8000

# Terminal 2
CUDA_VISIBLE_DEVICES=2,3 vllm serve mistralai/Mistral-Small-3.1-24B-Instruct-2503 \
  --tokenizer_mode mistral \
  --config_format mistral \
  --load_format mistral \
  --tool-call-parser mistral \
  --enable-auto-tool-choice \
  --limit_mm_per_prompt 'image=10' \
  --port 8001
```

Add the reverse proxy routes in /etc/nginx/sites-enabled/00-vllm.vhost:

```bash
server {
    listen 80;
    server_name your.domain.com;

    location /qwen/ {
        rewrite ^/qwen(/.*)$ $1 break;
        proxy_pass http://localhost:8000;
    }

    location /mistral/ {
        rewrite ^/mistral(/.*)$ $1 break;
        proxy_pass http://localhost:8001;
    }
}
```

Reload nginx:

```bash
nginx -t && systemctl restart nginx
```

## Important Notes

vLLM can only serve one model per instance.

Reverse proxy paths (e.g., /qwen) must be mapped manually to the correct model version.

Most OpenAI-compatible clients require the model ID in the request body. Ensure this matches the deployed model exactly.

You can omit the model parameter in requests only if the client allows it.

## Helpful Links

[vLLM Docs – Distributed Serving](https://docs.vllm.ai/en/latest/serving/distributed_serving.html)

[vLLM Docs – FAQ](https://docs.vllm.ai/en/latest/getting_started/faq.html)

[Hugging Face Tokens](https://huggingface.co/settings/tokens)

[vLLM Docs](https://docs.vllm.ai/en/latest/)
