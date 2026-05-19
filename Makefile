SHELL := C:/Program Files/Git/bin/bash.exe
ifeq (,$(wildcard $(SHELL)))
  SHELL := /bin/bash
endif
JAVAC = javac
JAVA  = java
OUT   = out

SRC := rpal20.java \
       src/lexer/TokenType.java src/lexer/Token.java src/lexer/Lexer.java \
       src/parser/ASTNode.java src/parser/Parser.java \
       src/standardizer/Standardizer.java \
       src/util/TreePrinter.java \
       src/cse/CSEMachine.java

# Default file; override with: make run FILE=path/to/program
FILE ?= rpal_test_programs/sample.rpal
# OUTPUT is unset by default; set to save: make output FILE=... OUTPUT=result.txt

all: $(OUT)/rpal20.class

$(OUT)/rpal20.class: $(SRC)
	@mkdir -p $(OUT)
	$(JAVAC) -d $(OUT) -sourcepath .:src $(SRC)

# Run program (no switches) — matches rpal.exe default.
run: all
	$(JAVA) -cp $(OUT) rpal20 $(FILE)

# Print AST.
ast: all
	$(JAVA) -cp $(OUT) rpal20 -ast $(FILE)

# Print Standardized AST.
sast: all
	$(JAVA) -cp $(OUT) rpal20 -sast $(FILE)
st: sast

# Print control structures + CSE trace.
cse: all
	$(JAVA) -cp $(OUT) rpal20 -cse $(FILE)

# Echo source then run.
l: all
	$(JAVA) -cp $(OUT) rpal20 -l $(FILE)

# Run all stages → print to terminal; if OUTPUT set, also save to file.
output: all
	@{ \
	  printf '========================================\n'; \
	  printf '  RESULT\n'; \
	  printf '========================================\n'; \
	  $(JAVA) -cp $(OUT) rpal20 $(FILE); \
	  printf '\n'; \
	  printf '========================================\n'; \
	  printf '  AST\n'; \
	  printf '========================================\n'; \
	  $(JAVA) -cp $(OUT) rpal20 -ast $(FILE); \
	  printf '\n'; \
	  printf '========================================\n'; \
	  printf '  STANDARDIZED AST\n'; \
	  printf '========================================\n'; \
	  $(JAVA) -cp $(OUT) rpal20 -sast $(FILE); \
	  printf '\n'; \
	  printf '========================================\n'; \
	  printf '  CSE MACHINE\n'; \
	  printf '========================================\n'; \
	  $(JAVA) -cp $(OUT) rpal20 -cse $(FILE); \
	  printf '\n'; \
	} | if [ -n "$(OUTPUT)" ]; then tee $(OUTPUT); else cat; fi

clean:
	rm -rf $(OUT)

.PHONY: all run ast sast st cse l output clean
