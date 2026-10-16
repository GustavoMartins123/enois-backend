# Mídia no S3 — como aplicar

O storage de mídia tem dois provedores e a escolha é **por variável de ambiente**, sem deploy de
código: `imagekit` (o de sempre) ou `s3` (o padrão novo). O que já está gravado no banco não muda
nunca — cada asset guarda o próprio endereço, então **asset antigo continua abrindo pelo ImageKit**
depois da virada. É esse o "fallback": ele é estrutural, não um caminho especial no código.

> **Estado:** o upload por URL assinada está implementado, mas **não foi testado contra um bucket
> real** (o S3 só existe em produção). A primeira virada de ambiente é a estreia — vale ler a seção
> "Primeira virada, com calma". A migração dos objetos antigos ainda **não** está implementada.

## O que a AWS precisa ter

| item | por quê |
|---|---|
| Bucket + região | onde os objetos vão morar |
| Permissão para o pod: `s3:PutObject`, `s3:GetObject`, `s3:DeleteObject` no prefixo | assinar a subida, ler e apagar de verdade (a exclusão de mídia passa a valer no bucket) |
| CORS no bucket liberando `PUT` de `https://enois.ia.br` | o arquivo sobe **do browser** direto para o bucket (o pod não recebe bytes) |
| Leitura pública no prefixo **ou** CloudFront na frente | o `cdnUrl` é gravado no banco e usado no feed e na miniatura: tem de ser um endereço estável, não uma URL assinada que expira |

Credencial **não** vai em variável nem em código: o pod resolve pela cadeia padrão do SDK dentro do
cluster (IRSA no service account). As tarefas de permissão acima são no **role do service account**.

## Variáveis de ambiente

| variável | exemplo | observação |
|---|---|---|
| `REDE_MEDIA_STORAGE` | `s3` | sem ela, o app segue em `imagekit` |
| `REDE_MEDIA_S3_BUCKET` | `enois-media` | obrigatória quando o storage é `s3` (o app **não sobe** sem ela — melhor falhar no boot do que gravar mídia apontando para lugar nenhum) |
| `REDE_MEDIA_S3_REGION` | `sa-east-1` | padrão `us-east-1` |
| `REDE_MEDIA_S3_PREFIX` | `uploads/` | padrão `uploads/`, a mesma forma dos objetos do ImageKit (facilita a migração). Prefixo `/` significa a **raiz** do bucket |
| `REDE_MEDIA_S3_PUBLIC_BASE_URL` | `https://cdn.enois.ia.br` | vazio = endereço do bucket na região |

## Primeira virada, com calma

1. Criar bucket, CORS, leitura (ou CloudFront) e as permissões no role do pod.
2. Subir **uma** réplica com as variáveis acima (ou aplicar no deployment e deixar o rollout normal).
3. Conferir o boot nos logs: com bucket/região ausentes o app falha com mensagem explícita — é o
   comportamento desejado.
4. Publicar **uma** foto em homologação e conferir na aba Rede do navegador:
   - a resposta de `POST /api/media/uploads` traz `"provider":"S3"` e um `uploadUrl` começando no
     bucket ou no CDN;
   - a subida é um `PUT` para esse endereço, com o `Content-Type` **idêntico** ao assinado;
   - o `cdnUrl` abre no navegador depois da subida.
5. Só então virar o ambiente inteiro. **Voltar atrás** é trocar `REDE_MEDIA_STORAGE` para `imagekit`
   e reiniciar: os objetos que já foram para o S3 continuam sendo servidos pelo endereço gravado no
   asset, então nada quebra.

Se a subida falhar com **403**, quase sempre é uma das duas: CORS do bucket sem `PUT` para
`https://enois.ia.br`, ou `Content-Type` diferente do assinado.

## Migração dos objetos antigos (ainda não implementada)

O desenho combinado, para quem for pegar: rodada incremental e idempotente — pega N assets cujo
`cdn_url` ainda é do ImageKit, baixa, sobe para o S3 no mesmo `objectKey`, **confere** e só então
atualiza o `cdn_url`; o objeto no ImageKit é preservado (é a segurança para voltar atrás). Rodadas
pequenas, repetidas, é o "aos poucos" — sem janela de manutenção e sem apagar nada no ImageKit.

## Limitações conhecidas

- **Sem as transformações do ImageKit.** O recorte `?tr=w-1200,h-630` e o quadro de vídeo
  `/ik-thumbnail.jpg` não existem no S3: a miniatura de post com foto passa a usar a imagem **crua**
  (o cartão de página não depende disso — ele é desenhado por nós) e vídeo fica sem poster até
  entrarem derivadas (ffmpeg no upload ou CloudFront + Lambda).
- Objetos órfãos no ImageKit continuam lá; apagar é decisão separada e depois de tudo conferido.

## Onde está o código

- `media/adapters/out/storage/S3StorageUrlFactory` — endereço dos objetos (sem rede, só composição).
- `media/adapters/out/storage/S3UploadSigner` — URL assinada (15 min, mesmo prazo da janela do asset).
- `media/adapters/out/storage/S3StorageConfig` — liga tudo isso só quando `REDE_MEDIA_STORAGE=s3`.
- `media/application/MediaService#createUpload` — devolve `provider` para o front decidir o caminho.
- front: `use-media-upload` escolhe entre `PUT` assinado (S3) e o multipart do ImageKit.
