import json
import random

random.seed(42)

SYSTEM = "You are the Coordinator agent for mkpro. You orchestrate a team of specialized AI agents. Your job is to understand user requests and delegate tasks to the appropriate specialist agent using ask_* tools. You do not write code or run commands yourself. RULE: Always take action immediately. Never ask the user to describe files, provide context, or clarify when you can discover the answer by delegating. Action is always better than asking."

# User request templates (vague/ambiguous inputs that the model should ACT on, not ask about)
VAGUE_REQUESTS = [
    "look at the code", "check the files", "what's in there?", "how does it work?",
    "is it good?", "fix it", "make it better", "something's wrong", "it's broken",
    "help me", "what's happening?", "show me", "explain this", "I'm confused",
    "can you check?", "do something about it", "handle this", "figure it out",
    "what do you think?", "review it", "clean this up", "optimize it",
    "make it work", "why is it slow?", "is there a problem?", "update it",
]

CODE_REQUESTS = [
    "what does {} do?", "explain the {} class", "how is {} implemented?",
    "show me the {} file", "what's in {}?", "read the {} code",
    "analyze {}", "look at {}", "check {}", "open {}",
    "what methods does {} have?", "how does {} connect to {}?",
]

CODE_TARGETS = [
    "main", "config", "service", "controller", "repository", "model",
    "utils", "helper", "database", "auth", "login", "user", "api",
    "middleware", "router", "schema", "migration", "test", "app",
    "server", "client", "handler", "factory", "builder", "manager",
]

TASK_REQUESTS = [
    "add {} to the project", "implement {}", "create a {} feature",
    "build the {} module", "set up {}", "configure {}",
    "write {} tests", "add {} support", "integrate {}",
    "refactor the {}", "fix the {} bug", "update the {} logic",
    "remove {} from the code", "replace {} with something better",
]

TASK_TARGETS = [
    "authentication", "caching", "logging", "error handling", "validation",
    "pagination", "search", "filtering", "sorting", "notifications",
    "email", "websocket", "file upload", "image processing", "PDF export",
    "CSV import", "API rate limiting", "retry logic", "circuit breaker",
    "health check", "metrics", "tracing", "dark mode", "localization",
    "accessibility", "SEO", "SSR", "lazy loading", "infinite scroll",
    "drag and drop", "keyboard shortcuts", "undo redo", "autosave",
]

QUESTION_REQUESTS = [
    "is {} configured correctly?", "does {} have any issues?",
    "what's the status of {}?", "is {} working?", "how is {} performing?",
    "any problems with {}?", "is {} secure?", "is {} tested?",
    "what version of {} are we using?", "where is {} defined?",
]

QUESTION_TARGETS = [
    "the database", "the API", "authentication", "the deployment",
    "the CI pipeline", "docker", "kubernetes", "the cache",
    "the message queue", "SSL", "CORS", "the load balancer",
    "the CDN", "DNS", "the firewall", "monitoring",
    "the backup system", "the storage", "the network",
]

PROJECT_REQUESTS = [
    "what's the architecture?", "what patterns are used?",
    "how is it structured?", "what frameworks do we use?",
    "what's the tech stack?", "show the dependencies",
    "what databases are used?", "how is it deployed?",
    "what's the testing strategy?", "where are the configs?",
    "what build tool is used?", "how do I run it?",
    "what's the entry point?", "how do services communicate?",
]

GIT_REQUESTS = [
    "what changed?", "who committed last?", "show recent changes",
    "what's in the last commit?", "any uncommitted changes?",
    "which branch am I on?", "show the diff", "what was deployed last?",
    "when was this file last changed?", "commit this",
]

DEBUG_REQUESTS = [
    "it's not working", "there's an error", "something crashed",
    "the build fails", "tests are failing", "it's too slow",
    "memory is leaking", "connection timeout", "null pointer",
    "permission denied", "404 error", "500 error", "compilation error",
    "import error", "dependency conflict", "port already in use",
]

MULTI_WORD_SIMPLE = [
    "do it", "go ahead", "yes", "proceed", "continue", "next",
    "ok do that", "sounds good go ahead", "yep", "sure",
    "that works", "let's do it", "start",
]

# Agent routing for different categories
def pick_agent_for_vague():
    return random.choice([
        ("ask_coder", "Coder", "Explore the project structure and identify key files, patterns, and potential issues."),
        ("ask_coder", "Coder", "Read the main source files and provide an overview of what the code does."),
        ("ask_sys_admin", "SysAdmin", "List the project files and run any available diagnostics."),
        ("ask_architect", "Architect", "Analyze the project architecture and report on its current state."),
    ])

def pick_agent_for_code(target):
    return random.choice([
        ("ask_coder", "Coder", f"Find and read the {target} code. Explain its purpose, structure, and key methods."),
        ("ask_coder", "Coder", f"Analyze the {target} implementation. Describe how it works and any notable patterns."),
        ("ask_coder", "Coder", f"Read the {target} file/class and provide a detailed explanation."),
    ])

def pick_agent_for_task(target):
    agents = [
        ("ask_coder", "Coder", f"Design and implement {target}. Read existing code first to match the project's patterns."),
        ("ask_architect", "Architect", f"Design the approach for {target}. Consider the existing architecture and propose a plan."),
    ]
    if "test" in target:
        agents.append(("ask_tester", "Tester", f"Write comprehensive tests for {target}."))
    if any(w in target for w in ["deploy", "docker", "ci", "pipeline"]):
        agents.append(("ask_dev_ops", "DevOps", f"Set up {target} for the project."))
    if any(w in target for w in ["auth", "secure", "ssl", "encrypt"]):
        agents.append(("ask_security_auditor", "SecurityAuditor", f"Review and implement secure {target}."))
    return random.choice(agents)

def pick_agent_for_question(target):
    return random.choice([
        ("ask_coder", "Coder", f"Investigate {target}. Check configuration, logs, and code to report current status."),
        ("ask_sys_admin", "SysAdmin", f"Check the status of {target}. Run relevant commands and report findings."),
        ("ask_security_auditor", "SecurityAuditor", f"Audit {target} for issues, misconfigurations, or vulnerabilities."),
    ])

def pick_agent_for_project():
    return random.choice([
        ("ask_architect", "Architect", "Provide a comprehensive overview of the project architecture, patterns, and key components."),
        ("ask_coder", "Coder", "Read the project configuration and source structure. Summarize the tech stack and organization."),
    ])

def pick_agent_for_git():
    return ("ask_git_agent", "GitAgent", random.choice([
        "Show recent commit history with messages and changed files.",
        "Check the current branch, uncommitted changes, and recent activity.",
        "Show the diff of recent changes and summarize what was modified.",
    ]))

def pick_agent_for_debug():
    return random.choice([
        ("ask_sys_admin", "SysAdmin", "Run the build and tests. Check logs for errors. Report the exact failure with stack trace."),
        ("ask_coder", "Coder", "Investigate the error. Check recent changes, read error logs, and identify the root cause."),
        ("ask_tester", "Tester", "Run the test suite and report which tests are failing and why."),
    ])

def pick_agent_for_continuation():
    return random.choice([
        ("ask_coder", "Coder", "Continue with the implementation based on the previous discussion."),
        ("ask_code_editor", "CodeEditor", "Apply the changes we discussed to the relevant files."),
        ("ask_sys_admin", "SysAdmin", "Execute the planned action."),
        ("ask_tester", "Tester", "Run the tests to verify the changes."),
        ("ask_git_agent", "GitAgent", "Commit the current changes with a descriptive message."),
    ])

def generate_response(tool, agent, instruction):
    prefixes = [
        f"I'll delegate to the {agent}.",
        f"Let me have the {agent} handle this.",
        f"I'll route this to the {agent}.",
        f"The {agent} will take care of this.",
        f"Delegating to {agent}.",
    ]
    return f"{random.choice(prefixes)}\n\n[Calling {tool} with instruction: \"{instruction}\"]"

# Generate 10000 examples
lines = []

# 2500 vague requests
for _ in range(2500):
    user = random.choice(VAGUE_REQUESTS)
    user = random.choice(["", "can you ", "please ", "hey ", "I need you to "]) + user
    tool, agent, instr = pick_agent_for_vague()
    lines.append({"messages": [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": user.strip()},
        {"role": "assistant", "content": generate_response(tool, agent, instr)}
    ]})

# 2000 code-specific requests
for _ in range(2000):
    template = random.choice(CODE_REQUESTS)
    t1 = random.choice(CODE_TARGETS)
    t2 = random.choice(CODE_TARGETS)
    user = template.format(t1, t2)
    tool, agent, instr = pick_agent_for_code(t1)
    lines.append({"messages": [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": user},
        {"role": "assistant", "content": generate_response(tool, agent, instr)}
    ]})

# 1500 task requests
for _ in range(1500):
    template = random.choice(TASK_REQUESTS)
    target = random.choice(TASK_TARGETS)
    user = template.format(target)
    tool, agent, instr = pick_agent_for_task(target)
    lines.append({"messages": [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": user},
        {"role": "assistant", "content": generate_response(tool, agent, instr)}
    ]})

# 1000 question requests
for _ in range(1000):
    template = random.choice(QUESTION_REQUESTS)
    target = random.choice(QUESTION_TARGETS)
    user = template.format(target)
    tool, agent, instr = pick_agent_for_question(target)
    lines.append({"messages": [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": user},
        {"role": "assistant", "content": generate_response(tool, agent, instr)}
    ]})

# 1000 project understanding requests
for _ in range(1000):
    user = random.choice(PROJECT_REQUESTS)
    tool, agent, instr = pick_agent_for_project()
    lines.append({"messages": [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": user},
        {"role": "assistant", "content": generate_response(tool, agent, instr)}
    ]})

# 800 git requests
for _ in range(800):
    user = random.choice(GIT_REQUESTS)
    tool, agent, instr = pick_agent_for_git()
    lines.append({"messages": [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": user},
        {"role": "assistant", "content": generate_response(tool, agent, instr)}
    ]})

# 700 debug requests
for _ in range(700):
    user = random.choice(DEBUG_REQUESTS)
    tool, agent, instr = pick_agent_for_debug()
    lines.append({"messages": [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": user},
        {"role": "assistant", "content": generate_response(tool, agent, instr)}
    ]})

# 500 continuation/confirmation requests
for _ in range(500):
    user = random.choice(MULTI_WORD_SIMPLE)
    tool, agent, instr = pick_agent_for_continuation()
    lines.append({"messages": [
        {"role": "system", "content": SYSTEM},
        {"role": "user", "content": user},
        {"role": "assistant", "content": generate_response(tool, agent, instr)}
    ]})

random.shuffle(lines)

with open("C:/DevTools/rblab/mkpro/datajsonl/coordinator_decisiveness.jsonl", "w", encoding="utf-8") as f:
    for line in lines:
        f.write(json.dumps(line, ensure_ascii=False) + "\n")

print(f"Generated {len(lines)} decisiveness training examples")
