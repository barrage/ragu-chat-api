# vLLM deployment setup

The setup assumes a Linux system with CUDA and Python preinstalled.
The specific versions are not of concern for this guide, so long as they are compatible.
Check the downstream documentation to properly configure this.

## Installation

- Install vLLM

```bash
pip install vllm
```

- Install Nginx

```bash
apt install nginx -y
```

- Start vLLM

```bash
vllm serve <MODEL> [--host <host>] [--port <port>]
```

Replace `<MODEL>` with the model code found on HuggingFace.

Note, some models require a Huggingface access token;

1. Go to [the tokens page](https://huggingface.co/settings/tokens).
2. Click "Create a new token" and configure to your heart's content.
3. Execute `huggingface-cli login` and enter your token.

## Reverse proxy

VLLM serves models on `localhost:8000` by default. VLLM
only [supports serving a single model at a time per server](https://docs.vllm.ai/en/latest/getting_started/faq.html#frequently-asked-questions),
therefore a reverse proxy is needed to route requests to the desired instance.

We can use the following nginx config file to route requests to the desired model:

```nginx
server {
    listen 80;
    server_name _;

    proxy_set_header Host $http_host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;

    location /<MODEL> {
        rewrite ^/<MODEL>(/.*)$ /$1 break;
        proxy_pass http://localhost:<VLLM_PORT>;
    }
}
```

For each model we are serving via VLLM, we have to specify a location directive. The location is arbitrary,
but must be known in advance to any upstream application that intends to communicate with it.

Replace `<MODEL>` with an arbitrary identifier to your liking.

For example, if you are serving the model `Qwen/Qwen2.5-1.5B-Instruct`, you can use `<MODEL> = qwen`, but upstream apps
will have to know that `qwen` maps to `Qwen/Qwen2.5-1.5B-Instruct`. This is because most OpenAI SDKs mandate a `model`
property in the request body. If we send any other model code than the one deployed on its respective VLLM, we will get
an error.

Therefore, applications will have to either:

- map the path (deployment ID) `qwen` to `Qwen/Qwen2.5-1.5B-Instruct` (model code) in their configuration and send the
  model code in the body, or
- completely omit the `model` parameter from the body.

The latter is preferred if possible.

This setup functions similarly to Azure deployments, where you specify the deployment name in the URL.
If you send `gpt-4` as the model parameter to Azure, but the deployment you are accessing is `gpt-4o-mini`, Azure will
silently ignore the model code in the body. VLLM will not ignore it and will error if you give it a model code that does
not match the one with which `vllm serve` was invoked, so just make sure to map the deployment name to the model code.

## Full example

In separate terminal windows, run:

```bash
vllm serve Qwen/Qwen2.5-1.5B-Instruct --enforce-eager --port 8000
```

```bash
vllm serve mistralai/Mistral-Small-3.1-24B-Instruct-2503 --tokenizer_mode mistral --config_format mistral --load_format mistral --tool-call-parser mistral --enable-auto-tool-choice --limit_mm_per_prompt 'image=10' --port 8001
```

If you want to send the LLMs to specific GPUs, you can prefix the commands with `CUDA_VISIBLE_DEVICES=<GPU_ID>`.
The variable accepts a comma-separated list of GPU IDs.

Place the following nginx config in `/etc/nginx/sites-enabled/vllm.vhost`

```nginx
server {
    listen 80;
    server_name _;

    # use the proxy_set_header directives from the previous section

    location /qwen {
        rewrite ^/qwen(/.*)$ /$1 break;
        proxy_pass http://localhost:8000;
    }

    location /mistral {
        rewrite ^/mistral(/.*)$ /$1 break;
        proxy_pass http://localhost:8001;
    }
}
```

Test the configuration

```bash
nginx -t
```

If all is good, restart Nginx so it picks up the vhost file

```bash
service nginx restart
```

Helpful links:

- [Distributed inference and serving](https://docs.vllm.ai/en/latest/serving/distributed_serving.html)
