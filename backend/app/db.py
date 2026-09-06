from sqlalchemy import inspect, text
from sqlmodel import Session, SQLModel, create_engine

from app.config import get_settings


engine = None


def get_engine():
    global engine
    if engine is None:
        engine = create_engine(
            get_settings().database_url, connect_args={"check_same_thread": False}
        )
    return engine


def init_engine() -> None:
    global engine
    settings = get_settings()
    engine = create_engine(settings.database_url, connect_args={"check_same_thread": False})


def create_all() -> None:
    SQLModel.metadata.create_all(get_engine())
    migrate_sqlite()


def migrate_sqlite() -> None:
    engine = get_engine()
    if engine.dialect.name != "sqlite":
        return
    inspector = inspect(engine)
    if "pairing_sessions" not in inspector.get_table_names():
        return
    columns = {column["name"] for column in inspector.get_columns("pairing_sessions")}
    if "creator_ephemeral_public_key" not in columns:
        with engine.begin() as conn:
            conn.execute(
                text("ALTER TABLE pairing_sessions ADD COLUMN creator_ephemeral_public_key VARCHAR")
            )


def get_session() -> Session:
    session = Session(get_engine())
    try:
        yield session
    finally:
        session.close()
