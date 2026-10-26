# Setup

Do this at the start of the workshop. The models for the agentic part are several gigabytes, and conference wifi is slow.

## 1. Check the prerequisites

You need these three already installed:

| Tool | Version | Check with |
|------|---------|------------|
| JDK | 21 or newer, see the note below | `java -version` |
| Maven | 3.9 or newer | `mvn -v` |
| Git | any recent version | `git --version` |

> [!NOTE]
> Chapter 15 calls public services of Spain, such as the register of veterinary medicines and the Junta de Andalucía, whose certificates come from FNMT-RCM, the Spanish state's certificate authority. Whether a JDK trusts it depends on the build. We checked these:
>
> | Trusts FNMT-RCM | JDK |
> |---|---|
> | yes | Microsoft Build of OpenJDK 21.0.11 and 21.0.12.1, Eclipse Temurin 21.0.13 and 25.0.5 |
> | no | OpenJDK 25.0.2 and 26.0.2.1 from jdk.java.net, Azul Zulu 21.0.11 |
>
> With a JDK of the second row, 9 of the 16 services of chapter 15 fail with `PKIX path building failed`. If you see that error, start the backend with the `mock` profile of chapter 15, which answers from recorded responses and works with any JDK.

The project also brings its own Maven, the wrapper `./mvnw`, which the instructions use. On Windows, type `mvnw.cmd` wherever the instructions say `./mvnw`.

## 2. Install Node.js

The frontend and MCPJam need Node.js 22 or 24. Check what you have:

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

## 5. Pull the models for the agentic part

The agentic part runs an agent on your laptop with [Ollama](https://ollama.com). Install it from [ollama.com/download](https://ollama.com/download) (tested with 0.32.15), then pull two models:

```bash
# the chat model, 5.3 GB
ollama pull granite4.1:8b

# optional: a second chat model to compare with in chapter 12, 5.2 GB
ollama pull qwen3:8b

# an embedding model tuned for GraphQL schemas, 639 MB, under the name the project uses
ollama pull hf.co/xthor/Qwen3-Embedding-0.6B-GraphQL:Q8_0
ollama cp hf.co/xthor/Qwen3-Embedding-0.6B-GraphQL:Q8_0 qwen3-graphql-embedder
```

The chat model needs about 8 GB of free memory, so a laptop with 16 GB of RAM works best. From chapter 14, the embedding model needs about 1.2 GB more, beside the chat model. On a machine with 24 GiB of graphics memory or more, Ollama gives each model a longer context, and the embedding model takes 4.8 GB there. To see how much RAM you have:

| System | Where |
|--------|-------|
| macOS | Apple menu > About This Mac |
| Windows | Settings > System > About > Installed RAM |
| Linux | `free -h` |

> **With 8 GB of RAM**, the operating system, the IDE, the browser and the application already use most of it, so `granite4.1:8b` may fail to load or answer very slowly. Pull `granite4.1:3b` instead (2.1 GB, [all sizes](https://ollama.com/library/granite4.1)), and in chapter 12 set `spring.ai.ollama.chat.model=granite4.1:3b`. In our runs of chapter 12's questions, it asked to sign in when it had to and booked "tomorrow" on the right day, but left the vet out of 12 bookings in 13. If neither runs well, chapter 12 shows how to use a hosted model instead.

RAM decides whether a model can run at all, and a graphics card decides how fast it runs. The card helps when the whole model fits in its own memory. When it does not fit, Ollama runs the model, or part of it, on the CPU, which works but is slower. On a Mac with Apple Silicon the CPU and GPU share the same memory, so RAM is the only number to check. While a model runs, `ollama ps` shows where it loaded, for example `100% GPU` or `48%/52% CPU/GPU` ([Ollama FAQ](https://docs.ollama.com/faq)).

`qwen3:8b` thinks before it answers. In our runs of chapter 12's questions on the same machine, it booked "next Wednesday" on the right day 5 times in 5, where `granite4.1:8b` did 2 times in 5, and a booking took it 17 to 54 seconds, where `granite4.1:8b` took 2 to 4. To try it, set `spring.ai.ollama.chat.model=qwen3:8b` in chapter 12.

## 6. Fetch the MCP Inspector and MCPJam

The MCP Inspector and MCPJam are browser clients for MCP servers. Both run with `npx`, which came with Node.js. Fetch each one once, so that it starts quickly during the workshop. MCPJam is the larger download, about 840 MB:

```bash
npx -y @modelcontextprotocol/inspector@2.9.0
```

```bash
npx -y @mcpjam/inspector@3.16.2
```

Each one opens a browser tab. Close the tab and stop it with `Ctrl+C` before you start the next.

Both use port 6274, and MCPJam's sign-in only works on that port, so during the workshop run one at a time: stop one with `Ctrl+C` before you start the other. To run both at once, move the Inspector to port 6284 with three environment variables, set in the terminal you start it from:

| Terminal | Command |
|--------|---------|
| macOS, Linux, Git Bash | `export CLIENT_PORT=6284 MCP_SANDBOX_PORT=6285 MCP_APP_ORIGIN_PORT=6288` |
| Windows PowerShell | `$env:CLIENT_PORT="6284"; $env:MCP_SANDBOX_PORT="6285"; $env:MCP_APP_ORIGIN_PORT="6288"` |
| Windows Command Prompt | `set "CLIENT_PORT=6284" & set "MCP_SANDBOX_PORT=6285" & set "MCP_APP_ORIGIN_PORT=6288"` |

Set them again in each new terminal you start the Inspector from.

## Checklist

- [ ] `java -version` shows 21 or newer
- [ ] `node -v` shows 22 or newer
- [ ] `./mvnw spring-boot:run` starts the application, and http://localhost:8080 shows Spring Petclinic
- [ ] http://localhost:5173 shows the frontend's home page
- [ ] `ollama list` shows `granite4.1:8b` (or `granite4.1:3b`) and `qwen3-graphql-embedder`
- [ ] `npx -y @modelcontextprotocol/inspector@2.9.0` opens the Inspector
- [ ] `npx -y @mcpjam/inspector@3.16.2` opens MCPJam
