import json
import os

# Caminho do arquivo enviado pelo Figma (ajuste se salvou em pasta)
json_path = 'tokens.json'
# Caminho de saída para o Jetpack Compose
output_path = 'app/src/main/java/com/example/app/ui/theme/Color.kt'

if os.path.exists(json_path):
    with open(json_path, 'r') as f:
        data = json.load(f)
    
    # Extrai o bloco de cores do JSON (o Tokens Studio organiza sob a chave 'color')
    colors = data.get('color', {})
    
    kotlin_code = "package com.example.app.ui.theme\n\nimport androidx.compose.ui.graphics.Color\n\n// Gerado automaticamente via Tokens Studio & GitHub Actions\n"
    
    for color_name, color_data in colors.items():
        hex_val = color_data.get('value', '').replace('#', '')
        if len(hex_val) == 6:
            hex_val = "FF" + hex_val # Adiciona opacidade total caso seja RGB simples
        
        if hex_val:
            kotlin_code += f"val {color_name.capitalize()} = Color(0x{hex_val.upper()})\n"
            
    # Cria a pasta do tema se não existir e escreve o arquivo Kotlin
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    with open(output_path, 'w') as f:
        f.write(kotlin_code)
    print("Color.kt gerado com sucesso!")
else:
    print("Arquivo tokens.json não encontrado.")
