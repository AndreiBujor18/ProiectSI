from fastapi import FastAPI
from pydantic import BaseModel
import requests

app = FastAPI()

OLLAMA_GENERATE_URL = "http://localhost:11434/api/generate"

class AssistRequest(BaseModel):
    task: str
    context: str
    model: str = "llama3.1"

class AssistResponse(BaseModel):
    text: str

@app.post("/assist", response_model=AssistResponse)
def assist(req: AssistRequest):
    task = (req.task or "").strip().lower()
    context = (req.context or "").strip()

    if "summary" in task:
        prompt = "Fa un rezumat scurt (max 6 bullet-uri) al conversatiei de mai jos:\n\n" + context
    else:
        prompt = "Propune 3 raspunsuri scurte (RO), prietenoase, pentru mesajele de mai jos:\n\n" + context

    payload = {"model": req.model, "prompt": prompt, "stream": False}
    r = requests.post(OLLAMA_GENERATE_URL, json=payload, timeout=30)
    r.raise_for_status()
    data = r.json()

    text = (data.get("response") or "").strip()
    return AssistResponse(text=text)
