# Setup

Do this before the workshop. The models for the second half are several gigabytes, and conference wifi is slow.

## 1. Check the prerequisites

You need these three already installed:

| Tool | Version | Check with |
|------|---------|------------|
| JDK | 21 or newer (tested with 21.0.12 and 25.0.2) | `java -version` |
| Maven | 3.9 or newer | `mvn -v` |
| Git | any recent version | `git --version` |

The project also brings its own Maven, the wrapper `./mvnw`, which the instructions use. On Windows, type `mvnw.cmd` wherever the instructions say `./mvnw`.

## 2. Install Node.js

The frontend needs Node.js 22 or 24 (20.19 at least). Check what you have:

```bash
node -v
```

If it is missing or older, install the LTS version from [nodejs.org](https://nodejs.org), or:

| System | Command |
|--------|---------|
| macOS (Homebrew) | `brew install node@24` |
| Windows | `winget install OpenJS.NodeJS.LTS` |
| Linux, or any system with nvm | `nvm install 24` |

## 3. Clone and start the backend

```bash
git clone https://github.com/graphqlguy/spring-petclinic-jOnTheBeach.git
cd spring-petclinic-jOnTheBeach
./mvnw spring-boot:run
```

The first run downloads Maven and the project's dependencies, which takes a few minutes. The application is ready when the log shows `Started PetClinicApplication`. Open http://localhost:8080 and you see the Spring Petclinic web pages. Stop it with `Ctrl+C`.

If port 8080 is taken, see [changing the port](../README.md#changing-the-port).

## 4. Install the frontend

In a second terminal, from the project folder:

```bash
cd frontend
npm install
npm run dev
```

Open http://localhost:5173. With the backend running, the home page says *"The backend is running. Its GraphQL endpoint arrives in chapter 1."* That is the starting point. Stop the frontend with `Ctrl+C`.

## 5. Pull the models for the second half

The second half runs an agent on your laptop with [Ollama](https://ollama.com). Install it from [ollama.com/download](https://ollama.com/download) (tested with 0.32.15), then pull two models:

```bash
# the chat model, 5.3 GB
ollama pull granite4.1:8b

# an embedding model tuned for GraphQL schemas, 639 MB, under the name the project uses
ollama pull hf.co/xthor/Qwen3-Embedding-0.6B-GraphQL:Q8_0
ollama cp hf.co/xthor/Qwen3-Embedding-0.6B-GraphQL:Q8_0 qwen3-graphql-embedder
```

The chat model needs about 8 GB of free memory, so a laptop with 16 GB of RAM works best. If yours cannot run it, chapter 12 shows how to use a hosted model instead.

## 6. Fetch the MCP Inspector

The MCP Inspector is a browser client for MCP servers. It runs with `npx`, which came with Node.js. Fetch it once, so that it starts quickly during the workshop:

```bash
npx -y @modelcontextprotocol/inspector@2.9.0
```

It opens a browser tab. Close the tab and stop it with `Ctrl+C`.

## Checklist

- [ ] `java -version` shows 21 or newer
- [ ] `./mvnw spring-boot:run` starts the application, and http://localhost:8080 shows Spring Petclinic
- [ ] http://localhost:5173 shows the frontend's home page
- [ ] `ollama list` shows `granite4.1:8b` and `qwen3-graphql-embedder`
- [ ] `npx -y @modelcontextprotocol/inspector@2.9.0` opens the Inspector
