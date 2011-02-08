package decaf;

import java.io.DataInputStream;
import java.io.InputStream;
import java6035.tools.CLI.CLI;

import antlr.RecognitionException;
import antlr.Token;
import antlr.collections.AST;

class Main {
	public static void main(String[] args) {
		try {
			CLI.parse(args, new String[0]);

			InputStream inputStream = args.length == 0 ? System.in
					: new java.io.FileInputStream(CLI.infile);

			if (CLI.target == CLI.SCAN) {
				DecafScanner lexer = new DecafScanner(new DataInputStream(
						inputStream));
				Token token;
				boolean done = false;
				while (!done) {
					try {
						for (token = lexer.nextToken(); token.getType() != DecafParserTokenTypes.EOF; token = lexer
								.nextToken()) {
							String type = "";
							String text = token.getText();

							switch (token.getType()) {
							case DecafScannerTokenTypes.ID:
								type = " IDENTIFIER";
								break;
							case DecafScannerTokenTypes.INT_LITERAL:
								type = " INTLITERAL";
								break;
							case DecafScannerTokenTypes.CHAR_LITERAL:
								type = " CHARLITERAL";
								break;
							case DecafScannerTokenTypes.STRING_LITERAL:
								type = " STRINGLITERAL";
								break;
							case DecafScannerTokenTypes.TK_true:
							case DecafScannerTokenTypes.TK_false:
								type = " BOOLEANLITERAL";
								break;
							}
							System.out.println(token.getLine() + type + " "
									+ text);
						}
						done = true;
					} catch (Exception e) {
						// print the error:
						System.out.println(CLI.infile + " " + e);
						lexer.consume();
					}
				}
			} else if (CLI.target == CLI.PARSE || CLI.target == CLI.DEFAULT) {
				try {
					DecafScanner lexer = new DecafScanner(new DataInputStream(
							inputStream));
					DecafParser parser = new DecafParser(lexer);
					parser.program();
					System.out.println("digraph g {");
					graphviz(null, parser.getAST());
					System.out.println("}");
				} catch (RecognitionException e) {
					e.printStackTrace();
					System.exit(1);
				}
			}

		} catch (Exception e) {
			// print the error:
			System.out.print(e.getClass().getCanonicalName());
			e.printStackTrace();
			System.out.println(CLI.infile + " " + e);
		}
	}

	public static void graphviz(AST parent, AST node) {
		if (node != null && node.getText() != null
				&& !node.getText().equals("null")) {
			System.out.printf("%s [label=\"%s\"];\n", String.valueOf(node
					.hashCode()), node.getText() + (node.getType() == DecafParserTokenTypes.METHOD_CALL ? "()" : ""));
			String sp = parent != null ? String.valueOf(parent.hashCode())
					: "root";
			System.out.printf("%s -> %s;\n", sp, String
					.valueOf(node.hashCode()));
			graphviz(parent, node.getNextSibling());
			if (node.getNumberOfChildren() != 0) {
				graphviz(node, node.getFirstChild());
			}
		}
	}
}
