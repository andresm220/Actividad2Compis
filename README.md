## Tokenizer (Java)

A simple lexical analyzer for a Java-like language.  
The lexer scans source code character by character (no regex, no explicit automata), produces tokens with line and column information, and builds a symbol table for identifiers.

### Requirements
- Java JDK 11 or newer

### Compile
```bash
javac Tokenizer.java
java Tokenizer


