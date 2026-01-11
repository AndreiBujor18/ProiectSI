# ProiectSI - Tema 2: Chat centralizat + Assistant LLM (Ollama)

## 1) Descrierea problemei
   **Proiectul** reprezintă un sistem de chat în model **centralizat (server–client)**, folosind **JADE (Java)**.
Fiecare utilizator este reprezentat de un agent client cu **interfață grafică (Swing)**. Toate mesajele sunt trimise către un
**server central**, care le rutează către destinatar (prin mesaj privat) sau către toți utilizatorii (prin broadcast).

Cerințe acoperite:
- **Model centralizat:** toate mesajele trec prin `ChatServerAgent`.
- **Minim 3 agenți JADE:** server + logger + cel puțin 2 clienți.
- **Minim 1 agent cu GUI:** `ChatClientAgent` (Swing).
- **DF (Directory Facilitator):** descoperirea serviciilor (server/logger/assistant).
- **Persistență:** log/istoric în fișier extern `chat-history.jsonl` (JSON Lines).
- **Închidere centralizată:** un client admin poate opri toți agenții.
- **Assistant LLM:** sugestii de răspuns în conversație și un scurt rezumat al dialogului prin Python (FastAPI) + Ollama (cu fallback dacă nu rulează).

---

## 2) Funcționalități
- Conectare cu username (validare username unic).
- Listă utilizatori online (update automat).
- Mesaje private către un utilizator selectat.
- Mesaje broadcast către toți utilizatorii existenți.
- Log în `chat-history.jsonl`.
- Shutdown centralizat (admin).
- Assistant: Suggest reply / Summarize chat (Ollama) + fallback.

---

## 3) Arhitectură (agenți)
- **ChatServerAgent** - server central: LOGIN/LOGOUT, USERLIST, rutare SEND->DELIVER, shutdown
- **ChatClientAgent** - client JADE cu GUI (Swing): trimitere/recepție mesaje
- **LoggerAgent** - persistă evenimentele în `chat-history.jsonl`
- **AssistantAgent** - cere sugestii/rezumat prin HTTP către serviciul Python

Flux:
1) Clientul găsește serverul în DF și se conectează (LOGIN).
2) Serverul răspunde LOGIN_OK/LOGIN_FAIL și trimite USERLIST tuturor.
3) Clientul trimite SEND către server; serverul livrează DELIVER către destinatari.
4) Serverul trimite LOG_EVENT către LoggerAgent (persistență).
5) Admin trimite SHUTDOWN_REQUEST; serverul trimite SHUTDOWN tuturor și se oprește curat.

---

## 4) Structura repo-ului
```
ProiectSI/
  src/ro/proiectsi/...
     /assistant-llm/
       assistant_server.py
       requirements.txt
```

---

## 5) Cerințe / Dependențe

### Java / JADE
- Java 8+
- Eclipse IDE (Java Project)
- JADE adăugat manual în Build Path:
  - `jade.jar`.
  - `jade-src.jar` și `jade-javadocs.jar` pentru IDE

> Notă: `jade.jar` nu este inclus în repo. Se adaugă local din Eclipse (Build Path).

### Assistant (opțional / bonus)
- Python 3.9+
- Ollama instalat și pornit
- Un model Ollama (llama3.1)

---

## 6) Instalare & Configurare (Eclipse)

### 6.1 Import proiect
1. `File -> Import -> General -> Existing Projects into Workspace`
2. Selectezi folderul proiectului și finalizezi importul.

### 6.2 Adăugare JADE în Build Path
1. Click dreapta pe proiect -> `Properties`
2. `Java Build Path -> Libraries -> Add External JARs`
3. Selectezi `jade.jar` (urmat de source/javadocs)

---

## 7) Lansare în execuție (fără Assistant)
1. Rulezi:
   - `src/ro/proiectsi/MainLauncher.java` -> `Run As -> Java Application`
2. În ferestrele GUI:
   - Connect în ambele (ex. `ana` și `maria`)
   - trimiți mesaje private și/sau broadcast
3. Verifici log-ul:
   - `chat-history.jsonl`, se creează/actualizează în working directory.

---

## 8) Assistant LLM (Ollama) - Instalare / Configurare / Rulare

### 8.1 Ollama + model
După ce se instalează, se scrie în terminal:
```bash
ollama pull llama3.1
ollama list

```

Test:
```bash
ollama run llama3.1 "Spune salut in română."
```

### 8.2 Pornește serverul Python (FastAPI)
Din folderul `assistant-llm/`:

**Windows (PowerShell)**
```powershell
cd assistant-llm
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -r requirements.txt
uvicorn assistant_server:app --host 127.0.0.1 --port 8000
```

Verificare:
- deschide `http://127.0.0.1:8000/docs`

### 8.3 Rulează aplicația Java
- Rulează `MainLauncher.java` -> Run As -> Java Application 
- Conectează-te în GUI
- Apasă:
  - **Suggest reply**
  - **Summarize chat**

> În cazul în care serviciul Python/Ollama nu rulează, `AssistantAgent` răspunde cu **fallback**, iar chat-ul rămâne funcțional.

---

## 9) Demonstrație (flux de utilizare)
1) Pornește aplicația (MainLauncher)  
2) Connect: `ana` și `maria`  
3) Privat: ana -> maria  
4) Broadcast: maria -> toți 
5) Assistant: Suggest + Summary  
6) Shutdown platform (admin)

---

## 10) Note despre fișiere generate
- `chat-history.jsonl` este generat la rularea proiectului.
- `assistant-llm/.venv` este generat local.

---
