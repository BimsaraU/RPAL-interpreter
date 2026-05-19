# RPAL Interpreter

Java implementation of RPAL (Right-reference Pedagogical Algorithmic Language). Reads a `.rpal` source file, lexes → parses → standardizes → flattens to control structures → executes on a CSE machine.

---

## Requirements

- **JDK 17+** (developed against JDK 26). Get one from any of:
  - Oracle JDK: https://www.oracle.com/java/technologies/downloads/
  - Eclipse Temurin: https://adoptium.net/
  - Microsoft Build of OpenJDK
- **GNU Make** (optional — only if you want `make`). Otherwise compile with raw `javac`.
- Any OS with a JDK (Windows / Linux / macOS).

Verify install:

```
javac -version
java  -version
```

Both must succeed. If `javac` is not recognized on Windows, add the JDK `bin` folder to `PATH` for the session:

```powershell
$env:Path = "C:\Program Files\Java\jdk-26.0.1\bin;" + $env:Path
```

---

## Persist `javac` on PATH (Windows)

Session-only (current PowerShell window only):

```powershell
$env:Path = "C:\Program Files\Java\jdk-26.0.1\bin;" + $env:Path
```

Permanent (user-level — survives reboot, applies to new shells):

```powershell
[Environment]::SetEnvironmentVariable(
  "Path",
  "C:\Program Files\Java\jdk-26.0.1\bin;" + [Environment]::GetEnvironmentVariable("Path","User"),
  "User")
```

Then **close and reopen** the terminal. Verify with `javac -version`.

GUI alternative: Start → "Edit environment variables for your account" → `Path` → New → paste `C:\Program Files\Java\jdk-26.0.1\bin` → OK. Reopen terminal.

Adjust the path if your JDK lives elsewhere — e.g. `C:\Program Files\Eclipse Adoptium\jdk-21.x.x\bin`.

Also set `JAVA_HOME` (some tools need it):

```powershell
[Environment]::SetEnvironmentVariable("JAVA_HOME","C:\Program Files\Java\jdk-26.0.1","User")
```

---

## Build

### Option A — Make

```
make
```

Produces compiled classes under `out/`.

### Option B — Raw javac (Windows PowerShell)

```powershell
mkdir out -Force | Out-Null
javac -d out -sourcepath src rpal20.java (Get-ChildItem -Recurse src -Filter *.java | ForEach-Object { $_.FullName })
```

### Option C — Raw javac (Linux / macOS / bash)

```bash
mkdir -p out
javac -d out -sourcepath src rpal20.java $(find src -name "*.java")
```

Clean:

```
make clean
```

---

## Run

```
java -cp out rpal20 [switches] <file>
```

| Switch        | Effect                                            |
|---------------|---------------------------------------------------|
| (none)        | Run program, print result. Matches `rpal.exe`.    |
| `-l`          | Echo source before running.                       |
| `-ast`        | Print AST only (dotted pre-order).                |
| `-sast` / `-st` | Print Standardized Tree only.                   |
| `-cse`        | Print control structures (δ list) + step trace.   |

Examples:

```
java -cp out rpal20 rpal_test_programs/rpal_01
java -cp out rpal20 -ast rpal_test_programs/rpal_03
java -cp out rpal20 -st  rpal_test_programs/rpal_06
java -cp out rpal20 -cse rpal_test_programs/rpal_01
```

### Run via Make

The Makefile takes `FILE=<path>` to choose the program. Default: `rpal_test_programs/sample.rpal`.

| Command                                           | Action                          |
|---------------------------------------------------|---------------------------------|
| `make run  FILE=rpal_test_programs/rpal_03`       | Execute, print result.          |
| `make ast  FILE=rpal_test_programs/rpal_03`       | Print AST.                      |
| `make sast FILE=rpal_test_programs/rpal_03`       | Print Standardized AST.         |
| `make cse  FILE=rpal_test_programs/rpal_03`       | Print control structures + trace. |
| `make l    FILE=rpal_test_programs/rpal_03`       | Echo source then run.           |
| `make run`                                        | Run default sample.             |
| `make clean`                                      | Remove `out/`.                  |

Each target auto-builds first if sources changed.

### How to run a test program (end-to-end)

1. Put the source anywhere — e.g. `rpal_test_programs/my_test.rpal`.
2. Build once: `make` (or the raw `javac` line above).
3. Run: `make run FILE=rpal_test_programs/my_test.rpal`.
4. Inspect stages if needed: `make ast FILE=...`, `make sast FILE=...`, `make cse FILE=...`.
5. Compare against `rpal.exe` reference output with `diff`:

```
java -cp out rpal20 rpal_test_programs/my_test.rpal > mine.out
rpal.exe rpal_test_programs/my_test.rpal             > ref.out
diff mine.out ref.out
```

---

## Project Layout

```
RPAL-Interpreter/
├── rpal20.java                 entry, arg parsing
├── Makefile
├── README.md
├── plan.md                     design plan
├── src/
│   ├── lexer/      Token, TokenType, Lexer
│   ├── parser/     Parser, ASTNode
│   ├── standardizer/ Standardizer
│   ├── cse/        CSEMachine (symbols, env, flatten, eval, built-ins)
│   └── util/       TreePrinter
├── out/                        compiled .class (after build)
└── rpal_test_programs/         sample programs
```

---

## Built-Ins

| Name           | Arity | Behavior                                       |
|----------------|-------|------------------------------------------------|
| `Print`        | 1     | print value (unescape `\n \t \\ \'`); ret dummy |
| `Conc`         | 2     | curried string concat                          |
| `Stem`         | 1     | first char of string                           |
| `Stern`        | 1     | string minus first char                        |
| `Order`        | 1     | tuple length (nil → 0)                         |
| `Null`         | 1     | true if nil / empty tuple                      |
| `Isinteger` `Istruthvalue` `Isstring` `Istuple` `Isfunction` `Isdummy` | 1 | type checks |
| `ItoS`         | 1     | int → string                                   |

---

## Sample RPAL Program

File: [rpal_test_programs/sample.rpal](rpal_test_programs/sample.rpal)

```
// Sample RPAL program. Factorial, tuple sum, string concat.

let rec fact n = n eq 0 -> 1 | n * fact (n-1)
in
let Sum T =
    let rec S i =
        i gr (Order T) -> 0 | T i + S (i+1)
    in S 1
in
let greet name = Conc 'Hello, ' (Conc name '!')
in
Print ( fact 5, Sum (10,20,30,40), greet 'RPAL' )
```

Expected output:

```
(120, 100, Hello, RPAL!)
```

RPAL has no statement separator — sequence effects via a single tuple-valued `Print`, or chain via `let _ = Print x in ...`.

Run:

```
java -cp out rpal20 rpal_test_programs/sample.rpal
```

---

## Notes

- Source files have no required extension; pick anything (`rpal_01`, `sample.rpal`, etc.).
- Strings use single quotes: `'hello'`. Escape sequences: `\n \t \\ \'`.
- Identifiers: letter then `[A-Za-z0-9_]*`. Comments: `// ...` to EOL.
- `rec` only binds one definition. Use `and` for mutual `let`s.
- `fn V1 V2 . E` defines an n-arg lambda; period required.
