FROM Python3.12
LABEL AUTOR HomaPlus
WORKDIR "/"

COPY "*.py" .
COPY "requirements.txt" .

RUN pip install --no-cache-dir -r requirements.txt

ENV host="localhost:23700"

CMD ["python", "./tclient.py", ${host}]