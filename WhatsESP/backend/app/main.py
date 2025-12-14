from fastapi import FastAPI

app = FastAPI(title="WhatsESP API", version="0.1.0")

@app.get("/status")
def status():
    return {"status": "ok"}
